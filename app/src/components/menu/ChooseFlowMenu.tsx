import {
  Badge,
  Box,
  Divider,
  IconButton,
  Menu,
  MenuItem,
  MenuProps,
  Tooltip,
  Typography
} from '@mui/material';
import { FlowType } from '../../types/FlowType';
import {
  Add,
  Check,
  Link,
  MoreHoriz,
  PlayForWork,
  Verified,
  Warning
} from '@mui/icons-material';
import { CloseCallbackType } from '../../types/CloseCallbackType';
import { useContext, useState } from 'react';
import { ProfileContext } from '../../contexts/UserContext';
import { green, red } from '@mui/material/colors';
import { comingSoonToast } from '../Toasts';

export type ChooseFlowMenuProps = MenuProps &
  CloseCallbackType & {
    flows: FlowType[];
    selectedFlow: FlowType;
    setSelectedFlow: React.Dispatch<React.SetStateAction<FlowType | undefined>>;
  };

export function ChooseFlowMenu({
  flows,
  selectedFlow,
  setSelectedFlow,
  closeStateCallback,
  ...props
}: ChooseFlowMenuProps) {
  const { profile } = useContext(ProfileContext);
  const [openNewFlowDialig, setOpenNewFlowDialig] = useState<boolean>(false);

  return (
    profile && (
      <>
        <Menu
          {...props}
          onClose={closeStateCallback}
          sx={{ mt: 1, maxWidth: 350, '.MuiMenu-paper': { borderRadius: 5 } }}
          anchorOrigin={{
            vertical: 'bottom',
            horizontal: 'left'
          }}>
          <MenuItem disabled key="payment_flow_title">
            <Typography fontWeight="bold" fontSize={16}>
              Payment Flows
            </Typography>
          </MenuItem>
          {flows &&
            flows.map((option) => (
              <MenuItem
                key={option.uuid}
                selected={option === selectedFlow}
                sx={{ alignContent: 'center' }}
                onClick={async () => {
                  setSelectedFlow(option);
                  closeStateCallback();
                }}>
                <Box
                  display="flex"
                  flexDirection="row"
                  alignItems="center"
                  justifyContent="space-between"
                  width="100%">
                  <Box
                    display="flex"
                    flexDirection="row"
                    alignItems="center"
                    justifyContent="flex-start">
                    <Box
                      display="flex"
                      flexDirection="row"
                      alignItems="center"
                      justifyContent="flex-start"
                      width={60}>
                      <Box width={30}>
                        {option === selectedFlow && <Check sx={{ color: green.A700 }} />}
                      </Box>
                      {option.uuid === profile.defaultFlow?.uuid && (
                        <Tooltip title="Default receiving payment flow">
                          <PlayForWork />
                        </Tooltip>
                      )}

                      {option.type === 'JAR' && (
                        <Tooltip title="Jar">
                          <Box src="/jar.png" component="img" sx={{ width: 20, height: 20 }} />
                        </Tooltip>
                      )}

                      {option.type === 'FARCASTER_VERIFICATION' && (
                        <Tooltip title="Farcaster Verification">
                          <Badge
                            overlap="circular"
                            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                            badgeContent={<Verified sx={{ width: 15, height: 15 }} />}>
                            <Box
                              src="/farcaster.svg"
                              component="img"
                              sx={{ width: 20, height: 20 }}
                            />
                          </Badge>
                        </Tooltip>
                      )}

                      {option.type === 'LINKED' && (
                        <Tooltip title="Linked Wallet">
                          <Badge
                            overlap="circular"
                            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                            badgeContent={<Link sx={{ width: 15, height: 15 }} />}>
                            <Box
                              src="/coinbase_smart_wallet.svg"
                              component="img"
                              sx={{ width: 20, height: 20 }}
                            />
                          </Badge>
                        </Tooltip>
                      )}

                      {option.wallets.length > 0 &&
                        option.wallets.find((w) => w.version === '1.3.0') && (
                          <Tooltip
                            arrow
                            title={
                              <Typography variant="subtitle2" color={red[400]} width="300">
                                Legacy flows will be decomissioned soon! <br />
                                Please, move your funds to other flows.
                              </Typography>
                            }>
                            <Warning fontSize="small" sx={{ color: red[400] }} />
                          </Tooltip>
                        )}
                    </Box>
                    <Typography variant="subtitle2" noWrap maxWidth={180}>
                      {option.title}
                    </Typography>
                  </Box>

                  {option === selectedFlow && (
                    <IconButton
                      size="small"
                      onClick={async (event) => {
                        event.stopPropagation();
                        comingSoonToast();
                      }}
                      sx={{ mx: 1 }}>
                      <MoreHoriz fontSize="small" />
                    </IconButton>
                  )}
                </Box>
              </MenuItem>
            ))}
          <Divider />
          <MenuItem
            disabled
            key="add_payment_flow"
            onClick={async () => {
              setOpenNewFlowDialig(true);
            }}>
            <Add fontSize="small" sx={{ width: 30, color: green.A700 }} />
            <Typography variant="subtitle2" color={green.A700}>
              New Payment Flow
            </Typography>
          </MenuItem>
        </Menu>
        {/* {openNewFlowDialig && (
          <NewFlowDialog
            profile={profile}
            open={openNewFlowDialig}
            closeStateCallback={async () => {
              setOpenNewFlowDialig(false);
            }}
          />
        )} */}
      </>
    )
  );
}
